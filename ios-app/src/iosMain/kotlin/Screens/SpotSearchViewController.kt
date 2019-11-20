package screens

import com.github.aakira.napier.Napier
import com.icerockdev.jetfinder.feature.spotSearch.presentation.SpotSearchViewModel
import common.FeedbackGenerator
import common.centerInSuperview
import common.fillSuperview
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSCoder
import platform.SpriteKit.SKSceneScaleMode
import platform.SpriteKit.SKView
import platform.UIKit.*
import views.SpotDistanceScene


class SpotSearchViewController : UIViewController {
    private val spotSearchStatusContainerView: UIView = UIView()
    private val statusLabel: UILabel = UILabel()
    private val instructionLabel: UILabel = UILabel()
    private val successImageView: UIImageView = UIImageView(UIImage.imageNamed("spotFound"))
    private val spotSearchViewContainer: SKView = SKView()
    private val spotSearchScene: SpotDistanceScene = SpotDistanceScene()
    private val feedbackGenerator: FeedbackGenerator = FeedbackGenerator()
    private val strengthLabel: UILabel = UILabel()

    private lateinit var viewModel: SpotSearchViewModel

    @OverrideInit
    constructor() : super(nibName = null, bundle = null)

    @OverrideInit
    constructor(coder: NSCoder) : super(coder)

    override fun viewDidLoad() {
        super.viewDidLoad()

        this.title = "Find a task"

        this.navigationItem.leftBarButtonItem = UIBarButtonItem(
            image = UIImage.imageNamed("back"),
            style = UIBarButtonItemStyle.UIBarButtonItemStylePlain,
            target = this,
            action = platform.darwin.sel_registerName("backButtonTapped")
        )

        this.view.backgroundColor = UIColor.whiteColor()

        this.view.addSubview(this.spotSearchStatusContainerView)
        this.spotSearchStatusContainerView.translatesAutoresizingMaskIntoConstraints = false
        this.spotSearchStatusContainerView.centerXAnchor.constraintEqualToAnchor(this.view.centerXAnchor)
            .setActive(true)
        this.spotSearchStatusContainerView.centerYAnchor.constraintEqualToAnchor(this.view.centerYAnchor)
            .setActive(true)

        val spotBackgroundImageView: UIImageView =
            UIImageView(UIImage.imageNamed("spotSearchBackground"))
        this.spotSearchStatusContainerView.addSubview(spotBackgroundImageView)
        spotBackgroundImageView.fillSuperview()

        this.spotSearchStatusContainerView.addSubview(this.spotSearchViewContainer)
        this.spotSearchViewContainer.fillSuperview()
        this.spotSearchViewContainer.backgroundColor = UIColor.clearColor
        this.spotSearchViewContainer.showsFPS = false
        this.spotSearchViewContainer.showsNodeCount = false

        this.spotSearchStatusContainerView.addSubview(this.successImageView)
        this.successImageView.centerInSuperview()

        this.statusLabel.font = UIFont.boldSystemFontOfSize(20.0)
        this.statusLabel.textAlignment = NSTextAlignmentCenter
        this.statusLabel.textColor = UIColor.colorNamed("blackTextColor")!!

        this.instructionLabel.font = UIFont.systemFontOfSize(14.0)
        this.instructionLabel.textAlignment = NSTextAlignmentCenter
        this.instructionLabel.numberOfLines = 0
        this.instructionLabel.lineBreakMode = NSLineBreakByWordWrapping
        this.instructionLabel.textColor = UIColor.colorNamed("blackLightTextColor")!!

        val labelsStackView: UIStackView = UIStackView()
        labelsStackView.translatesAutoresizingMaskIntoConstraints = false
        labelsStackView.axis = UILayoutConstraintAxisVertical
        labelsStackView.spacing = 10.0
        labelsStackView.addArrangedSubview(this.statusLabel)
        labelsStackView.addArrangedSubview(this.instructionLabel)

        this.view.addSubview(labelsStackView)

        labelsStackView.topAnchor.constraintEqualToAnchor(
            this.spotSearchStatusContainerView.bottomAnchor,
            constant = 30.0
        ).setActive(true)
        labelsStackView.centerXAnchor.constraintEqualToAnchor(this.view.centerXAnchor)
            .setActive(true)
        labelsStackView.widthAnchor.constraintLessThanOrEqualToAnchor(
            this.view.widthAnchor,
            multiplier = 0.8,
            constant = 0.0
        ).setActive(true)

        this.strengthLabel.translatesAutoresizingMaskIntoConstraints = false
        this.view.addSubview(this.strengthLabel)

        this.strengthLabel.leadingAnchor.constraintEqualToAnchor(this.view.leadingAnchor).setActive(true)
        this.strengthLabel.topAnchor.constraintEqualToAnchor(this.view.topAnchor, constant = 100.0).setActive(true)

        this.strengthLabel.textColor = UIColor.redColor
        this.strengthLabel.font = UIFont.boldSystemFontOfSize(30.0)

        this.spotSearchScene.distance = 0.0f
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        this.spotSearchScene.scaleMode = SKSceneScaleMode.SKSceneScaleModeResizeFill
        this.spotSearchViewContainer.presentScene(this.spotSearchScene)
    }

    override fun viewWillAppear(animated: Boolean) {
        super.viewWillAppear(animated)

        this.navigationController?.setNavigationBarHidden(false, animated = false)

        this.spotSearchViewContainer.paused = false
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)

        if (this.isMovingFromParentViewController()) {
            viewModel.onCleared()
        }

        this.spotSearchViewContainer.paused = true
    }

    fun bindViewModel(viewModel: SpotSearchViewModel) {
        this.viewModel = viewModel

        viewModel.nearestBeaconDistance.addObserver { distance: Int? ->
            Napier.d("distance: $distance")

            this.strengthLabel.text = "$distance"

            val maxDistance: Int = 100

            this.spotSearchScene.distance = (distance ?: 0) / maxDistance.toFloat()

            this.feedbackGenerator.feedback(this.spotSearchScene.distance.toDouble())
        }

        viewModel.isSearchMode.addObserver { searchMode: Boolean ->
            if (searchMode) {
                this.statusLabel.text = "Searching..."
                this.successImageView.setHidden(true)
                this.spotSearchViewContainer.setHidden(false)
            } else {
                this.statusLabel.text = "Spot found"
                this.successImageView.setHidden(false)
                this.spotSearchViewContainer.setHidden(true)
            }
        }

        viewModel.hintText.addObserver { text: String ->
            this.instructionLabel.text = text
        }
    }

    @ObjCAction
    fun backButtonTapped() {
        this.navigationController?.popViewControllerAnimated(true)
    }
}
